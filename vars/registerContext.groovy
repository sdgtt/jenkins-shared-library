import sdg.IStepExecutor;
import sdg.ioc.ContextRegistry

def call(steps) {
    ContextRegistry.registerDefaultContext(steps)
}
