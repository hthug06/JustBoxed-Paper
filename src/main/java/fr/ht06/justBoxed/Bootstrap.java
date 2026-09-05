package fr.ht06.justBoxed;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;

class Bootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext bootstrapContext) {
        bootstrapContext.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
//            event.registrar().register(BoxedCommand.createCommand().build(), List.of("box"));
//            event.registrar().register(AdminBoxedCommand.createCommand().build(), List.of("adminbox", "abox"));
        });
    }
}
