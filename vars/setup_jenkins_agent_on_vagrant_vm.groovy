def call(String IP, Integer Port=8080) {
    sh 'wget https://gist.githubusercontent.com/ccraluca/1a031a1bd71ede37bac01c10c66197c3/raw/da35163cefc6a3ad8e137ce6cfb6831c6f884f5c/jenkins_win_vm_setup.ps1 -O jenkins_win_vm_setup.ps1'
    sh 'vagrant upload jenkins_win_vm_setup.ps1 "C:\"'
    SPort = Integer.toString(Port)
    sh "vagrant winrm --command \"C:\\jenkins_win_vm_setup.ps1 http://${IP}:${SPort}/swarm/swarm-client.jar win-vm http://${IP}:${SPort}/ C:\\jenkins\""
}
