package cn.sab1e.autosync;

public class UpdateSyncTimeEvent {
    private String message;

    public UpdateSyncTimeEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
