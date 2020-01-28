/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.jdbc.dialect.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.annotations.common.util.StringHelper;
import org.hibernate.boot.registry.selector.spi.StrategySelectionException;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfoSource;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;

/**
 * Standard implementation of the {@link DialectFactory} service.
 *
 * @author Steve Ebersole
 */
public class DialectFactoryImpl implements DialectFactory, ServiceRegistryAwareService {
	private StrategySelector strategySelector;
	private DialectResolver dialectResolver;

	@Override
	public void injectServices(ServiceRegistryImplementor serviceRegistry) {
		this.strategySelector = serviceRegistry.getService( StrategySelector.class );
		this.dialectResolver = serviceRegistry.getService( DialectResolver.class );
	}

	/**
	 * Intended only for use from testing.
	 *
	 * @param dialectResolver The DialectResolver to use
	 */
	public void setDialectResolver(DialectResolver dialectResolver) {
		this.dialectResolver = dialectResolver;
	}

	@Override
	public Dialect buildDialect(Map configValues, DialectResolutionInfoSource resolutionInfoSource) throws HibernateException {
		final Object dialectReference = configValues.get( AvailableSettings.DIALECT );
		if ( !isEmpty( dialectReference ) ) {
			return constructDialect( dialectReference, resolutionInfoSource );
		}
		else {
			return determineDialect( resolutionInfoSource );
		}
	}

	@SuppressWarnings("SimplifiableIfStatement")
	private boolean isEmpty(Object dialectReference) {
		if ( dialectReference != null ) {
			// the referenced value is not null
			if ( dialectReference instanceof String ) {
				// if it is a String, it might still be empty though...
				return StringHelper.isEmpty( (String) dialectReference );
			}
			return false;
		}
		return true;
	}

	private Dialect constructDialect(Object dialectReference, DialectResolutionInfoSource resolutionInfoSource) {
		try {
			Dialect dialect = strategySelector.resolveStrategy(
					Dialect.class,
					dialectReference,
					(Dialect) null,
					(dialectClass) -> {
						try {
							try {
								if (resolutionInfoSource != null) {
									return dialectClass.getConstructor(DialectResolutionInfo.class).newInstance(
											resolutionInfoSource.getDialectResolutionInfo()
									);
								}
							}
							catch (NoSuchMethodException nsme) {}
							return dialectClass.newInstance();
						}
						catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
							throw new StrategySelectionException(
									String.format( "Could not instantiate named dialect class [%s]", dialectClass.getName() ),
									e
							);
						}
					}
			);
			if ( dialect == null ) {
				throw new HibernateException( "Unable to construct requested dialect [" + dialectReference + "]" );
			}
			return dialect;
		}
		catch (HibernateException e) {
			throw e;
		}
		catch (Exception e) {
			throw new HibernateException( "Unable to construct requested dialect [" + dialectReference + "]", e );
		}
	}

	/**
	 * Determine the appropriate Dialect to use given the connection.
	 *
	 * @param resolutionInfoSource Access to DialectResolutionInfo used to resolve the Dialect.
	 *
	 * @return The appropriate dialect instance.
	 *
	 * @throws HibernateException No connection given or no resolver could make
	 * the determination from the given connection.
	 */
	private Dialect determineDialect(DialectResolutionInfoSource resolutionInfoSource) {
		if ( resolutionInfoSource == null ) {
			throw new HibernateException(
					"Unable to determine Dialect without JDBC metadata "
					+ "(please set 'javax.persistence.jdbc.url', 'hibernate.connection.url', or 'hibernate.dialect')"
			);
		}

		final DialectResolutionInfo info = resolutionInfoSource.getDialectResolutionInfo();
		final Dialect dialect = dialectResolver.resolveDialect( info );

		if ( dialect == null ) {
			throw new HibernateException(
					"Unable to determine Dialect for " + info.getDatabaseName() + " "
					+ info.getDatabaseMajorVersion() + "." + info.getDatabaseMinorVersion()
					+ " (please set 'hibernate.dialect' or register a Dialect resolver)"
			);
		}

		return dialect;
	}
}
