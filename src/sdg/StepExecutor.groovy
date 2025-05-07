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
    Integer sh(String command){
        return this._steps.sh(command)
    }

    @Override
    String sh(Map kwargs = [:]){
        return this._steps.sh(kwargs)
    }
    
    @Override
    Integer bat(String command){
        return this._steps.bat(command)
    }
    
    @Override
    String bat(Map kwargs = [:]){
        return this._steps.bat(kwargs)
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
    void echo(String message){
        this._steps.echo(message)
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

    @Override
    void retry(int count, Closure cls){
        this._steps.retry(count, cls)
    }

    @Override
    void archiveArtifacts(Map kwargs = [:]) {
        this._steps.archiveArtifacts(kwargs)
    }

    @Override
    boolean isUnix() {
        this._steps.isUnix()
    }

    @Override
    boolean fileExists(String file) {
        return this._steps.fileExists(file)
    }

    @Override
    String readFile(String file) {
        return this._steps.readFile(file)
    }

    @Override
    void writeFile(Map kwargs = [:]) {
        this._steps.writeFile(kwargs)
    }

    @Override
    void dir(String dir, Closure cls) {
        this._steps.dir(dir, cls)
    }

    @Override
    void unstable(String message) {
        this._steps.unstable(message)
    }

    @Override
    void sleep(int seconds){
        this._steps.sleep(seconds)
    }

    private Map _mockEnv = null

    Map getEnv() {
        return _mockEnv ?: (_steps?.env ?: [:])
    }

    void setMockEnv(Map env) {
        this._mockEnv = env
    }
}
