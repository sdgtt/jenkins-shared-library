package sdg

import sdg.IStepExecutor
import jenkins.model.Jenkins

class StepExecutor implements IStepExecutor{
    // this will be provided by the vars script and 
    // let's us access Jenkins steps
    private _steps 

    StepExecutor(steps) {
        this._steps = steps
    }

    @Override
    int sh(String command) {
        this._steps.sh returnStatus: true, script: "${command}"
    }

    @Override
    void error(String message) {
        this._steps.error(message)
    }

    @Override
    void stage(String name, Closure cls) {
        this._steps.stage(name,cls)
    }

    @Override
    void println(String message){
        this._steps.println(message)
    }

    @Override
    Map<String, Object> getGauntEnv(
        String hdlBranch,
        String linuxBranch,
        String bootPartitionBranch,
        String firmwareVersion,
        String bootfile_source
    ){
        this._steps.getGauntEnv(
            hdlBranch,
            linuxBranch,
            bootPartitionBranch,
            firmwareVersion,
            bootfile_source,
        )
    }

        
}
