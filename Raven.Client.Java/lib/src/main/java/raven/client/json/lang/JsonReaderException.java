package raven.client.json.lang;

import java.io.IOException;

public class JsonReaderException extends RuntimeException {

  public JsonReaderException() {
    super();
  }

  public JsonReaderException(String message, Throwable cause) {
    super(message, cause);
  }

  public JsonReaderException(String message) {
    super(message);
  }

  public JsonReaderException(Throwable cause) {
    super(cause);
  }

}
