package sdg

import jenkins.model.Jenkins

interface IStepExecutor {

    // Jenkins steps as needed
    Integer sh(String command)
    String sh(Map kwargs)
    Integer bat(String command)
    String bat(Map kwargs)
    void error(String message)
    void stage(String name, Closure cls)
    void echo(String message)
    void println(String message)
    Map<String, Object> getGauntEnv(
        String hdlBranch,
        String linuxBranch,
        String bootPartitionBranch,
        String firmwareVersion,
        String bootfile_source
    )
    void retry(int count, Closure cls)
    void archiveArtifacts(Map kwargs) 
    boolean isUnix()
    boolean fileExists(String file)
    String readFile(String file)
    void writeFile(Map kwargs)
    void dir(String dir, Closure cls)
    void unstable(String message)
    void sleep(int seconds)
}