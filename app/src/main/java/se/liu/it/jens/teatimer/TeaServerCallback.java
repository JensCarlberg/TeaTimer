package se.liu.it.jens.teatimer;

public interface TeaServerCallback {
    void ok(String result);
    void fail(int code, Throwable throwable);
}
