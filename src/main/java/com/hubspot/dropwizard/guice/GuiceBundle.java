package com.hubspot.dropwizard.guice;

import java.util.List;

import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.*;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.util.Modules;
import com.sun.jersey.guice.JerseyServletModule;
import com.yammer.dropwizard.ConfiguredBundle;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.config.Environment;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class GuiceBundle<T extends Configuration> implements ConfiguredBundle<T> {

	private final AutoConfig autoConfig;
	private final List<Module> modules;
  private final List<Module> overlayModules;
    private final Stage stage;
    private Injector injector;
	private JerseyContainerModule jerseyContainerModule;
	private DropwizardEnvironmentModule dropwizardEnvironmentModule;
	private Optional<Class<T>> configurationClass;
	private GuiceContainer container;

  public static class Builder<T extends Configuration> {
		private AutoConfig autoConfig;
		private List<Module> modules = Lists.newArrayList();
		private List<Module> overlayModules = Lists.newArrayList();
		private Optional<Class<T>> configurationClass = Optional.absent();
    private com.google.inject.Stage stage;

		public Builder<T> addModule(Module module) {
			checkNotNull(module);
			modules.add(module);
			return this;
		}

    public Builder<T> stage(Stage stage) {
      this.stage = stage;
      return this;
    }

		public Builder<T> overlayModule(Module module) {
			checkNotNull(module);
			overlayModules.add(module);
			return this;
		}

		public Builder<T> setConfigClass(Class<T> clazz) {
			configurationClass = Optional.of(clazz);
			return this;
		}

		public Builder<T> enableAutoConfig(String... basePackages) {
			checkNotNull(basePackages.length > 0);
			checkArgument(autoConfig == null, "autoConfig already enabled!");
			autoConfig = new AutoConfig(basePackages);
			return this;
		}
		
		public GuiceBundle<T> build() {
			return new GuiceBundle<T>(autoConfig, modules, overlayModules, configurationClass, stage  );
		}

	}
	
	public static <T extends Configuration> Builder<T> newBuilder() {
		return new Builder<T>();
	}

	private GuiceBundle(AutoConfig autoConfig,
          List<Module> modules,
          final List<Module> overlayModules,
          Optional<Class<T>> configurationClass,
          final Stage stage) {
    checkNotNull(modules);
		checkArgument(!modules.isEmpty());
		this.modules = modules;
    this.overlayModules = overlayModules;
		this.autoConfig = autoConfig;
		this.configurationClass = configurationClass;
    this.stage = checkNotNull(stage);
	}
	
	@Override
	public void initialize(Bootstrap<?> bootstrap) {
		container = new GuiceContainer();
		jerseyContainerModule = new JerseyContainerModule(container);
		if (configurationClass.isPresent()) {
			dropwizardEnvironmentModule = new DropwizardEnvironmentModule<T>(configurationClass.get());
		} else {
			dropwizardEnvironmentModule = new DropwizardEnvironmentModule<Configuration>(Configuration.class);
		}
		modules.add(Modules.override(new JerseyServletModule()).with(jerseyContainerModule));
		modules.add(dropwizardEnvironmentModule);
		injector = Guice.createInjector(stage, Modules.override(modules).with(overlayModules));
		if (autoConfig != null) {
			autoConfig.initialize(bootstrap, injector);
		}
	}

	@Override
	public void run(final T configuration, final Environment environment) {
		container.setResourceConfig(environment.getJerseyResourceConfig());
		environment.setJerseyServletContainer(container);
		environment.addFilter(GuiceFilter.class, configuration.getHttpConfiguration().getRootPath());
		setEnvironment(configuration, environment);

		if (autoConfig != null) {
			autoConfig.run(environment, injector);
		}
	}

	@SuppressWarnings("unchecked")
	private void setEnvironment(final T configuration, final Environment environment) {
		dropwizardEnvironmentModule.setEnvironmentData(configuration, environment);
	}

	public Injector getInjector() {
		return injector;
	}
}
