package dev.fleetops.shared;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
 public final HttpStatus status;
 public ApiException(HttpStatus status, String detail) { super(detail); this.status=status; }
 public static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT,"Concurrent change or duplicate identifier; refresh and retry"); }
 public static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"Resource not found"); }
 public static ApiException invalid(String detail) { return new ApiException(HttpStatus.BAD_REQUEST,detail); }
}
