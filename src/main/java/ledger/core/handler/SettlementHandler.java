package ledger.core.handler;

import ledger.core.EventHandler;
import ledger.event.Settlement;

/** Not implemented yet. */
public class SettlementHandler implements EventHandler<Settlement> {

    @Override
    public Class<Settlement> eventType() {
        return Settlement.class;
    }

    @Override
    public void handle(Settlement event, int day) {
        throw new UnsupportedOperationException("Settlements are not supported yet");
    }
}
