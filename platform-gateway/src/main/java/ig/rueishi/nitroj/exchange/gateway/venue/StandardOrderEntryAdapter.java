package ig.rueishi.nitroj.exchange.gateway.venue;

import ig.rueishi.nitroj.exchange.gateway.ExecutionRouterImpl;
import ig.rueishi.nitroj.exchange.messages.CancelOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.NewOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.OrdType;
import ig.rueishi.nitroj.exchange.messages.OrderStatusQueryCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.ReplaceOrderCommandDecoder;
import ig.rueishi.nitroj.exchange.messages.Side;
import ig.rueishi.nitroj.exchange.messages.TimeInForce;
import ig.rueishi.nitroj.exchange.registry.IdRegistry;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.NewOrderSingleEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderCancelReplaceRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderCancelRequestEncoder;
import ig.rueishi.nitroj.exchange.fix.fixt11.fix50sp2.builder.OrderStatusRequestEncoder;
import uk.co.real_logic.artio.builder.Encoder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * Shared generated-FIXT.1.1/FIX 5.0SP2 implementation of venue order-entry routing.
 *
 * <p>Responsibility: maps normalized SBE order commands into generated Artio
 * order-entry encoders and sends them with retry/back-pressure handling. Role in
 * system: this class now owns the generated FIX encoder dependency that used to
 * live in gateway core, while {@link ExecutionRouterImpl} delegates through the
 * {@link VenueOrderEntryAdapter} interface. Relationships: {@link IdRegistry}
 * resolves instrument IDs to FIX symbols, {@link OrderEntryPolicy} supplies
 * venue-specific enrich/replace rules, and callbacks connect exhausted sends to
 * metrics and rejection publication. Lifecycle: one adapter is created for an
 * acquired session and reused on the gateway egress thread. Design intent:
 * preserve Coinbase's current FIX 5.0SP2 order-entry behavior while creating
 * the V11 extension seam for future FIX versions and venue policies.
 */
public final class StandardOrderEntryAdapter implements VenueOrderEntryAdapter {
    public static final int MAX_SEND_ATTEMPTS = 3;
    public static final long BACK_PRESSURE_SLEEP_NANOS = 1_000L;
    private static final int DECIMAL_SCALE = 8;
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final DateTimeFormatter FIX_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS");

    private final ExecutionRouterImpl.FixSender sender;
    private final IdRegistry idRegistry;
    private final String account;
    private final Runnable backPressureCounter;
    private final ExecutionRouterImpl.RejectPublisher rejectPublisher;
    private final Clock clock;
    private final OrderEntryPolicy policy;
    private final NewOrderSingleEncoder newOrderEncoder = new NewOrderSingleEncoder();
    private final OrderCancelRequestEncoder cancelEncoder = new OrderCancelRequestEncoder();
    private final OrderCancelReplaceRequestEncoder replaceEncoder = new OrderCancelReplaceRequestEncoder();
    private final OrderStatusRequestEncoder statusEncoder = new OrderStatusRequestEncoder();

    public StandardOrderEntryAdapter(
        final ExecutionRouterImpl.FixSender sender,
        final IdRegistry idRegistry,
        final String account,
        final Runnable backPressureCounter,
        final ExecutionRouterImpl.RejectPublisher rejectPublisher,
        final Clock clock,
        final OrderEntryPolicy policy) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.idRegistry = Objects.requireNonNull(idRegistry, "idRegistry");
        this.account = Objects.requireNonNull(account, "account");
        this.backPressureCounter = Objects.requireNonNull(backPressureCounter, "backPressureCounter");
        this.rejectPublisher = Objects.requireNonNull(rejectPublisher, "rejectPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public void sendNewOrder(final NewOrderCommandDecoder command) {
        newOrderEncoder.resetMessage();
        newOrderEncoder.account(account);
        newOrderEncoder.clOrdID(Long.toString(command.clOrdId()));
        newOrderEncoder.handlInst('1');
        newOrderEncoder.symbol(symbol(command.instrumentId()));
        newOrderEncoder.side(fixSide(command.side()));
        newOrderEncoder.transactTime(timestampBytes());
        newOrderEncoder.orderQty(command.qtyScaled(), DECIMAL_SCALE);
        newOrderEncoder.ordType(fixOrdType(command.ordType()));
        if (command.ordType() == OrdType.LIMIT) {
            newOrderEncoder.price(command.priceScaled(), DECIMAL_SCALE);
        }
        newOrderEncoder.timeInForce(fixTimeInForce(command.timeInForce()));
        policy.enrichNewOrder(command, newOrderEncoder);

        sendWithRetry(newOrderEncoder, command.clOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendCancel(final CancelOrderCommandDecoder command) {
        cancelEncoder.resetMessage();
        cancelEncoder.origClOrdID(Long.toString(command.origClOrdId()));
        copyVenueOrderId(command, cancelEncoder);
        cancelEncoder.clOrdID(Long.toString(command.cancelClOrdId()));
        cancelEncoder.symbol(symbol(command.instrumentId()));
        cancelEncoder.side(fixSide(command.side()));
        cancelEncoder.transactTime(timestampBytes());
        cancelEncoder.orderQty(command.originalQtyScaled(), DECIMAL_SCALE);
        policy.enrichCancel(command, cancelEncoder);

        sendWithRetry(cancelEncoder, command.cancelClOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendReplace(final ReplaceOrderCommandDecoder command) {
        if (!policy.nativeReplaceSupported()) {
            return;
        }

        replaceEncoder.resetMessage();
        copyVenueOrderId(command, replaceEncoder);
        replaceEncoder.origClOrdID(Long.toString(command.origClOrdId()));
        replaceEncoder.clOrdID(Long.toString(command.newClOrdId()));
        replaceEncoder.handlInst('1');
        replaceEncoder.symbol(symbol(command.instrumentId()));
        replaceEncoder.side(fixSide(command.side()));
        replaceEncoder.transactTime(timestampBytes());
        replaceEncoder.orderQty(command.newQtyScaled(), DECIMAL_SCALE);
        replaceEncoder.ordType('2');
        replaceEncoder.price(command.newPriceScaled(), DECIMAL_SCALE);
        replaceEncoder.timeInForce('1');
        policy.enrichReplace(command, replaceEncoder);

        sendWithRetry(replaceEncoder, command.newClOrdId(), command.venueId(), command.instrumentId());
    }

    @Override
    public void sendStatusQuery(final OrderStatusQueryCommandDecoder command) {
        statusEncoder.resetMessage();
        statusEncoder.clOrdID(Long.toString(command.clOrdId()));
        copyVenueOrderId(command, statusEncoder);
        statusEncoder.symbol(symbol(command.instrumentId()));
        statusEncoder.side(fixSide(command.side()));
        policy.enrichStatusQuery(command, statusEncoder);

        sendWithRetry(statusEncoder, command.clOrdId(), command.venueId(), command.instrumentId());
    }

    private boolean sendWithRetry(final Encoder encoder, final long clOrdId, final int venueId, final int instrumentId) {
        for (int attempt = 0; attempt < MAX_SEND_ATTEMPTS; attempt++) {
            final long result = sender.trySend(encoder);
            if (result >= 0) {
                return true;
            }
            LockSupport.parkNanos(BACK_PRESSURE_SLEEP_NANOS);
        }

        backPressureCounter.run();
        rejectPublisher.publishRejected(clOrdId, venueId, instrumentId);
        return false;
    }

    private String symbol(final int instrumentId) {
        final String symbol = idRegistry.symbolOf(instrumentId);
        if (symbol == null) {
            throw new IllegalArgumentException("Unknown instrumentId: " + instrumentId);
        }
        return symbol;
    }

    private byte[] timestampBytes() {
        return FIX_TIMESTAMP.format(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static char fixSide(final Side side) {
        return side == Side.BUY ? '1' : '2';
    }

    private static char fixOrdType(final OrdType ordType) {
        return ordType == OrdType.MARKET ? '1' : '2';
    }

    private static char fixTimeInForce(final TimeInForce timeInForce) {
        return switch (timeInForce) {
            case DAY -> '0';
            case GTC -> '1';
            case IOC -> '3';
            case FOK -> '4';
            default -> '0';
        };
    }

    private static void copyVenueOrderId(
        final CancelOrderCommandDecoder command,
        final OrderCancelRequestEncoder encoder) {
        final int length = command.venueOrderIdLength();
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
            return;
        }
        final byte[] orderId = new byte[length];
        command.getVenueOrderId(orderId, 0, length);
        encoder.orderID(orderId);
    }

    private static void copyVenueOrderId(
        final ReplaceOrderCommandDecoder command,
        final OrderCancelReplaceRequestEncoder encoder) {
        final int length = command.venueOrderIdLength();
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
            return;
        }
        final byte[] orderId = new byte[length];
        command.getVenueOrderId(orderId, 0, length);
        encoder.orderID(orderId);
    }

    private static void copyVenueOrderId(
        final OrderStatusQueryCommandDecoder command,
        final OrderStatusRequestEncoder encoder) {
        final int length = command.venueOrderIdLength();
        if (length == 0) {
            encoder.orderID(EMPTY_BYTES);
            return;
        }
        final byte[] orderId = new byte[length];
        command.getVenueOrderId(orderId, 0, length);
        encoder.orderID(orderId);
    }
}
