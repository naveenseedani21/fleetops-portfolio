package dev.fleetops.shared;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class RequestIds extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String id=UUID.randomUUID().toString(); res.setHeader("X-Request-ID",id);
  try(var ignored=MDC.putCloseable("request.id",id)) { chain.doFilter(req,res); }
 }
}
