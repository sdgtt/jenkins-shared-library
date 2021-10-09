/**
 * Interface to vagrant VMs
 */
package sdg
// import sdg.NominalException

class Vagrant {
    private box
    private debug
    Vagrant (String box='ubuntu/focal64', boolean debug=false) {
        this.box = box
    }
    def call(String commands) {

        println('Commands to run: '+commands)
        // Bring VM up from snapshot (for speed)
        this.create_vagrantfile()
        sh 'vagrant up'

        // Run commands
        this.send_to_vm(commands)

        // Destroy VM
        sh 'vagrant destroy'
        sh 'rm Vagrantfile'
    }
    private def create_vagrantfile(){
        sh 'echo \'Vagrant.configure("2") do |config|\' > Vagrantfile'
        sh 'echo \'  config.vm.box = \"'+this.box+'\"\' >  Vagrantfile'
        sh 'echo \'end\' >> Vagrantfile'
        
        println("Generated Vagrantfile")
    }
    private def send_to_vm(String commands){
        sh "vagrant ssh -- -t '"+commands+"'"
    }
    private sh(String cmd){
        def stdout = cmd.execute()
        println(stdout)
    }
}
