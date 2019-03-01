package org.mitre.quaerite.connectors;

public class SearchServerException extends Exception {

    public SearchServerException(String msg) {
        super(msg);
    }

    public SearchServerException(Exception e) {
        super(e);
    }
}
