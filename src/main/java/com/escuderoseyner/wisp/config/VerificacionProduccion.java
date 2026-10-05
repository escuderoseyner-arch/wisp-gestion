package com.escuderoseyner.wisp.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;

// Solo en producción: impide arrancar si la URL de la base de datos desactiva el SSL.
// (application-prod.properties ya pide sslMode=REQUIRED; esto evita que un DB_URL mal copiado lo anule.)
// Es un BeanFactoryPostProcessor porque Spring los ejecuta ANTES de crear cualquier otro bean:
// así la revisión ocurre antes de abrir la primera conexión, que si no viajaría sin cifrar.
@Component
@Profile("prod")
public class VerificacionProduccion implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment entorno;

    @Override
    public void setEnvironment(Environment entorno) {
        this.entorno = entorno;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String url = entorno.getProperty("spring.datasource.url", "").toLowerCase(Locale.ROOT).replace(" ", "");
        if (url.contains("sslmode=disabled") || url.contains("sslmode=preferred") || url.contains("usessl=false")) {
            throw new IllegalStateException(
                    "DB_URL desactiva el SSL. En producción la conexión con la base de datos debe ir cifrada "
                            + "(usa sslMode=REQUIRED o VERIFY_CA).");
        }
    }
}
