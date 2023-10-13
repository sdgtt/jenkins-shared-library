def call(String IP, Integer Port=8080) {
    sh 'wget https://gist.githubusercontent.com/tfcollins/e6050b317feb963d2cce022a448401d3/raw/fe058fe76aba7fd96046e9e99497f0fd2eb5436a/jenkins_win_vm_setup.ps1 -O jenkins_win_vm_setup.ps1'
    sh 'vagrant upload jenkins_win_vm_setup.ps1 "C:\"'
    SPort = Integer.toString(Port)
    sh "vagrant winrm --command \"C:\\jenkins_win_vm_setup.ps1 http://${IP}:${SPort}/swarm/swarm-client.jar win-vm http://${IP}:${SPort}/ C:\\jenkins\""
}
