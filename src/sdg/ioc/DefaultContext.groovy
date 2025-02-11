package sdg.ioc

import sdg.IStepExecutor
import sdg.StepExecutor

class DefaultContext implements IContext, Serializable {
    // the same as in the StepExecutor class
    private _steps

    DefaultContext(steps) {
        this._steps = steps
    }

    @Override
    IStepExecutor getStepExecutor() {
        return new StepExecutor(this._steps)
    }

    @Override
    Boolean isDefault(){
        return true
    }

}
