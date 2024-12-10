package sdg

import jenkins.model.Jenkins

interface IStepExecutor {

    // Jenkins steps as needed 
    int sh(String command)
    void error(String message)
    void stage(String name, Closure cls)
    void println(String message)
    Map<String, Object> getGauntEnv(
        String hdlBranch,
        String linuxBranch,
        String bootPartitionBranch,
        String firmwareVersion,
        String bootfile_source
    )
}