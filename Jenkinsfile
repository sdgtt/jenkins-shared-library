// Pipeline

println("Build branch ${env.BRANCH_NAME}")
library "tfc-lib@${env.BRANCH_NAME}"

def dependencies = ["nebula","libiio","libiio-py"]
def hdlBranch = "NA"
def linuxBranch = "NA"
def bootPartitionBranch = "release"
def firmwareVersion = 'v0.32'
def bootfile_source = 'artifactory' // options: sftp, artifactory, http, local
def harness = getGauntlet(dependencies, hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)

//udpate repos
// harness.set_env('nebula_repo','https://github.com/tfcollins/nebula.git')
// harness.set_env('nebula_branch','master')

//update first the agent with the required deps
harness.update_agents()

//update nebula config
def jobs = [:]
for (agent in harness.gauntEnv.agents_online) {
    println('Agent: ' + agent)
    def agent_name = agent
    jobs[agent_name] = {
        node(agent_name) {
            stage('Update Nebula Config') {
                sh 'if [ -d "nebula-config" ]; then rm -Rf nebula-config; fi'
                sh 'git clone -b v0.1 https://github.com/kimpaller/nebula-config.git'
                cmd = 'sudo mv nebula-config/' + agent_name + ' /etc/default/nebula'
                sh cmd
            }
        }
    }
}

stage('Configure Agents') {
    parallel jobs
}


//set other test parameters
harness.set_nebula_debug(true)
harness.set_enable_docker(true)
harness.set_required_hardware(["zynq-zed-adv7511-ad9364-fmcomms4"])
harness.set_docker_args([]) 
harness.set_nebula_local_fs_source_root("artifactory.analog.com")


// Set stages (stages are run sequentially on agents)
harness.add_stage(harness.stage_library("UpdateBOOTFiles"),'stopWhenFail')

// // Test stage
harness.add_stage(harness.stage_library("LinuxTests"),'continueWhenFail')
harness.add_stage(harness.stage_library('PyADITests'),'continueWhenFail')
harness.add_stage(harness.stage_library('LibAD9361Tests'),'continueWhenFail')

// // Go go
harness.run_stages()