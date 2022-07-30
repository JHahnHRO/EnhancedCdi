package io.github.jhahnhro.enhancedcdi.context.businessprocess;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

public class BusinessProcessExtension implements Extension {

    BusinessProcessContext context;

    void addContext(@Observes AfterBeanDiscovery abd) {
        context = new BusinessProcessContext();
        abd.addContext(context);
    }
}
