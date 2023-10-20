def call(String project_vm, cls) {

    // project_vm: Since vagrant manages environments based on folders this variable is used as a subfolder
    //    were common projects can reuse the same vm    
    VM_ENV_ROOT_FOLDER = '/vagrant_envs'
    folder = VM_ENV_ROOT_FOLDER + "/"+ project_vm

    dir(folder) {

    stage('Vagrant Setup'){
    // Get related Vagrant file for environment
    sh 'rm Vagrantfile || true'
    sh 'wget https://gist.githubusercontent.com/tfcollins/3e520c840df4d9a278e6c2b32dba58e5/raw/9b2eefd1ea3d7a1b0f4aad1943efd6b1a7b303f1/Vagrantfile'
       
    // Check VM is available

    // Bring up VM
    echo "Resuming Dev VM"

    status = sh(returnStdout: true, script: "vagrant status | grep default").trim()
    status = status.toLowerCase()
    println("Got vagrant status: "+status)

    if (status.contains('shutdown')) {
        sh 'vagrant up'
    }
    else if (status.contains('running')) {
        echo "VM already running"
    }
    else if (status.contains('paused')) {
        tries = 5
        for (i = 0; i<tries; i++) {
            try {
                sh 'vagrant resume default'
                break
            }
            catch(Exception ex) {
                println(ex)
                println('Resume failed. Using we did not wait enough. Retrying')
                sleep 5
                status = sh(returnStdout: true, script: "vagrant status | grep default").trim()
                status = status.toLowerCase()
                if (status.contains('running')) {
                    break
                }
                if (i == (tries - 1)) {
                    sh 'vagrant resume default'
                }
            }
        }
    }
    else if (status.contains('inaccessible')) {
        sh 'virsh shutdown '+project_vm+'_default'
        sh 'vagrant halt'
        sh 'vagrant up'
    }
    else { // pmsuspended
        sh 'vagrant halt'
        sleep 5
        sh 'vagrant up'
    }

    // Restart Jenkins service
    sh 'vagrant winrm -c "net stop JenkinsAgent"'
    //sh 'vagrant winrm -c "del /S C:\\jenkins\\log\\* /Q"'
    sh 'vagrant winrm -c "net start JenkinsAgent"'

    echo "Giving some time for agent to be available to Jenkins"
    sleep 20
    }
    
    // Run closure
    try {
      stage('VM Runtime'){
      name = check_node('win-vm')
      run_closure(name, cls)
      }
    }
    finally {
      stage('Vagrant Cleanup'){
      // Cleanup
      name = check_node('win-vm')
      markNodeOffline(node, "Vagrant box suspend")
      echo "Loading snapshot and putting in suspended state"
      sh 'vagrant snapshot restore default initial-state'
      sh 'vagrant suspend'
      }
    }
      
    }//dir
}

@NonCPS
def markNodeOffline(node, message) {
    node = getCurrentNode(node)
    computer = node.toComputer()
    computer.setTemporarilyOffline(true, new hudson.slaves.OfflineCause.ByCLI("Run only one build for each node"))
    computer.doChangeOfflineCause(message)
    computer = null
    node = null
}
​
@NonCPS
def getCurrentNode(nodeName) {
  for (node in Jenkins.instance.nodes) {
      if (node.getNodeName() == nodeName) {
        echo "Found node for $nodeName"
        return node
    }
  }
  throw new Exception("No node for $nodeName")
}

// //Example
//
// node("lab0") {
//     stage("One"){
//         vagrant_vm('win10_generic'){
//             stage('test1'){
//                 bat 'hostname'
//                 bat '''
//                 call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvarsamd64_x86.bat"
//                 cmake --version
//                 '''
//             }
//         }
//     }
// }
