package tech.wenisch.kairos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Locale;

/**
 * Suppresses IOException / ClientAbortException noise that Tomcat logs via the DispatcherServlet
 * when an SSE client disconnects while the server is trying to write an event.
 *
 * Spring's ResponseBodyEmitter catches the IOException internally and removes the emitter, but
 * Tomcat's async error dispatch pipeline still routes the failure through DispatcherServlet where
 * it would otherwise be logged at ERROR level. This resolver intercepts that dispatch first.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ClientDisconnectExceptionResolver implements HandlerExceptionResolver {

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {
        if (isClientDisconnect(ex)) {
            log.trace("Suppressed client-disconnect exception for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
            // Return an empty ModelAndView to signal "handled" — prevents DispatcherServlet from logging it.
            return new ModelAndView();
        }
        return null;
    }

    private boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            // Tomcat wraps all client-abort IOExceptions in ClientAbortException
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }
            if (current instanceof IOException) {
                String message = current.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase(Locale.ROOT);
                    if (lower.contains("broken pipe")
                            || lower.contains("connection reset")
                            || lower.contains("connection abort")
                            || lower.contains("forcibly closed")
                            || lower.contains("softwaregesteuert")
                            || lower.contains("abgebrochen")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
