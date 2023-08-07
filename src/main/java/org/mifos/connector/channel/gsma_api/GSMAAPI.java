package org.mifos.connector.channel.gsma_api;

import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;

public interface GSMAAPI {

    @GetMapping("/channel/gsma")
    String gsma(HttpServletResponse response) throws Exception;
}
