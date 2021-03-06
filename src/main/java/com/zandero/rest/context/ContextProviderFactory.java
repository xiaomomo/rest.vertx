package com.zandero.rest.context;

import com.zandero.rest.data.ClassFactory;
import com.zandero.rest.data.RouteDefinition;
import com.zandero.rest.exception.ClassFactoryException;
import com.zandero.rest.exception.ContextException;
import com.zandero.rest.injection.InjectionProvider;
import com.zandero.utils.Assert;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

import javax.ws.rs.core.Context;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Storage of context providers
 */
public class ContextProviderFactory extends ClassFactory<ContextProvider> {

	@Override
	protected void init() {

		// nothing to
	}

	public ContextProvider getContextProvider(InjectionProvider provider,
	                                          Class clazzType,
	                                          Class<? extends ContextProvider> aClass) throws ClassFactoryException {


		return get(provider, clazzType, aClass, null);
	}

	public void register(Class<?> aClass, Class<? extends ContextProvider> clazz) {
		super.register(aClass, clazz);
	}

	public void register(Class<?> aClass, ContextProvider instance) {
		super.register(aClass, instance);
	}

	public static boolean hasContext(Class<?> clazz) {

		// check if any class members are injected
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			Annotation found = field.getAnnotation(Context.class);
			if (found != null) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Provides vertx context of desired type if possible
	 *
	 * @param definition   route definition
	 * @param type         context type
	 * @param defaultValue default value if given
	 * @param context      to extract value from
	 * @return found context or null if not found
	 */
	public static Object provideContext(RouteDefinition definition,
	                                    Class<?> type,
	                                    String defaultValue,
	                                    RoutingContext context) throws ContextException {

		// vert.x context
		if (type.isAssignableFrom(HttpServerResponse.class)) {
			return context.response();
		}

		if (type.isAssignableFrom(HttpServerRequest.class)) {
			return context.request();
		}

		if (type.isAssignableFrom(RoutingContext.class)) {
			return context;
		}

		if (type.isAssignableFrom(Vertx.class)) {
			return context.vertx();
		}

		if (type.isAssignableFrom(User.class)) {
			return context.user();
		}

		// internal context
		if (type.isAssignableFrom(RouteDefinition.class)) {
			return definition;
		}

		// browse through context storage
		if (context.data() != null && context.data().size() > 0) {

			Object item = context.data().get(getContextKey(type));
			if (item != null) { // found in storage ... return
				return item;
			}
		}

		if (defaultValue != null) {
			// check if type has constructor that can be used with defaultValue ...
			// and create Context type on the fly constructed with defaultValue
			try {
				return ClassFactory.constructType(type, defaultValue);
			}
			catch (ClassFactoryException e) {
				throw new ContextException("Can't provide @Context of type: " + type + ". " + e.getMessage());
			}
		}

		throw new ContextException("Can't provide @Context of type: " + type);
	}

	public static void injectContext(Object instance, RouteDefinition definition, RoutingContext routeContext) throws ContextException {

		if (instance == null) {
			return;
		}

		if (hasContext(instance.getClass())) {
			Field[] fields = instance.getClass().getDeclaredFields();
			for (Field field : fields) {
				Annotation found = field.getAnnotation(Context.class);
				if (found != null) {

					Object context = provideContext(definition, field.getType(), null, routeContext);
					try {
						field.setAccessible(true);
						field.set(instance, context);
					}
					catch (IllegalAccessException e) {
						throw new ContextException("Can't provide @Context for: " + field.getType() + " - " + e.getMessage());
					}
				}
			}
		}
	}

	public static String getContextKey(Object object) {

		Assert.notNull(object, "Expected object but got null!");
		if (object instanceof Class) {
			return "RestRouter-" + ((Class)object).getName();
		}

		return "RestRouter-" + object.getClass().getName();
	}
}
