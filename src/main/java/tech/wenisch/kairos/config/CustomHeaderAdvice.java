package tech.wenisch.kairos.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import tech.wenisch.kairos.service.CustomHeaderService;

@ControllerAdvice
@RequiredArgsConstructor
public class CustomHeaderAdvice {

    private final CustomHeaderService customHeaderService;

    @ModelAttribute("customHeadersHtml")
    public String customHeadersHtml(HttpServletRequest request) {
        boolean isAdminPage = request.getRequestURI().startsWith(request.getContextPath() + "/admin");
        return customHeaderService.getHtmlForPage(isAdminPage);
    }
}
