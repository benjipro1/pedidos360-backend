package cl.benji.pedidos360.msproductos.config;

import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * En Spring Boot 4.x se elimino la propiedad
 * {@code spring.jpa.defer-datasource-initialization} (ver advertencia de
 * versiones en CLAUDE.md). Ademas, por defecto Boot 4.x hace que el
 * EntityManagerFactory dependa del inicializador de scripts SQL
 * ({@code JpaDependsOnDatabaseInitializationDetector}), asumiendo el patron
 * "schema.sql define el esquema, JPA espera". Nuestro caso es el opuesto:
 * Hibernate genera el esquema y data.sql debe esperarlo a el.
 *
 * Esta clase invierte esa relacion: quita la dependencia automatica de
 * entityManagerFactory hacia el inicializador de data.sql, y en su lugar
 * hace que data.sql dependa de que el EntityManagerFactory (y por lo tanto
 * el esquema) ya exista.
 */
@Configuration
public class DatabaseInitializationOrderConfig implements BeanFactoryPostProcessor, Ordered {

    private static final String SQL_INITIALIZER_BEAN = "dataSourceScriptDatabaseInitializer";
    private static final String ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory";

    @Override
    public int getOrder() {
        // Debe ejecutarse despues del BeanFactoryPostProcessor de Boot que
        // agrega la dependencia automatica JPA -> inicializador SQL.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition(SQL_INITIALIZER_BEAN)
                || !beanFactory.containsBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)) {
            return;
        }

        BeanDefinition entityManagerFactory = beanFactory.getBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN);
        String[] dependsOn = entityManagerFactory.getDependsOn();
        if (dependsOn != null) {
            String[] sinInicializadorSql = Stream.of(dependsOn)
                    .filter(nombre -> !SQL_INITIALIZER_BEAN.equals(nombre))
                    .toArray(String[]::new);
            entityManagerFactory.setDependsOn(sinInicializadorSql);
        }

        BeanDefinition sqlInitializer = beanFactory.getBeanDefinition(SQL_INITIALIZER_BEAN);
        String[] dependsOnActual = sqlInitializer.getDependsOn();
        String[] conEntityManagerFactory = dependsOnActual == null
                ? new String[] { ENTITY_MANAGER_FACTORY_BEAN }
                : Arrays.copyOf(dependsOnActual, dependsOnActual.length + 1);
        if (dependsOnActual != null) {
            conEntityManagerFactory[dependsOnActual.length] = ENTITY_MANAGER_FACTORY_BEAN;
        }
        sqlInitializer.setDependsOn(conEntityManagerFactory);
    }
}
