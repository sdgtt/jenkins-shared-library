/**
 * Interface to vagrant VMs
 */
package sdg
import sdg.NominalException

class Vagrant {
    private box
    private debug
    Vagrant (String box='ubuntu/focal64', boolean debug=false) {
        this.box = box
    }
    def call(String commands) {

        echo 'Commands to run: '+commands
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
        File file = new File("Vagrantfile")
        file << 'Vagrant.configure("2") do |config|\n'
        file << '  config.vm.box = \"'+this.box+'\"\n'
        file << 'end'
        
        println("Generated Vagrantfile")
        println file.text
    }
    private def send_to_vm(String commands){
        sh "vagrant ssh -- -t '"+commands+"'"
    }
}
