package ledger.core.handler;

import ledger.core.EventHandler;
import ledger.event.Authorization;

/** Not implemented yet. */
public class AuthorizationHandler implements EventHandler<Authorization> {

    @Override
    public Class<Authorization> eventType() {
        return Authorization.class;
    }

    @Override
    public void handle(Authorization event, int day) {
        throw new UnsupportedOperationException("Authorizations are not supported yet");
    }
}
