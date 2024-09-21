package cn.sab1e.autosync;

public class UpdateUploadNumberEvent {
    private String message;

    public UpdateUploadNumberEvent(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
