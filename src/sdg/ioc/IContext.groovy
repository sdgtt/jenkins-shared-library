package sdg.ioc

import sdg.IStepExecutor

interface IContext {
    IStepExecutor getStepExecutor()
    Boolean isDefault()
    Map getEnv()
}
