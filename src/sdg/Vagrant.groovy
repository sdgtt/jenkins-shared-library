/**
 * Interface to vagrant VMs
 */
package sdg
// import sdg.NominalException
import jenkins.*
import jenkins.model.*
import hudson.*
import hudson.model.* 

class Vagrant implements Serializable {
    private box
    private debug
    private ctx
    Vagrant (ctx, String box='ubuntu/focal64', boolean debug=false) {
        this.ctx = ctx
        this.box = box
        this.debug = debug
    }
    def call(String commands) {

        ctx.println('Commands to run: '+commands)
        // Bring VM up from snapshot (for speed)
        this.create_vagrantfile()
        ctx.sh 'vagrant up'

        // Run commands
        this.send_to_vm(commands)

        // Destroy VM
        ctx.sh 'vagrant destroy -f'
        ctx.sh 'rm Vagrantfile'
    }
    private def create_vagrantfile(){
        ctx.sh 'echo \'Vagrant.configure("2") do |config|\' > Vagrantfile'
        ctx.sh 'echo \'  config.vm.box = \"'+this.box+'\"\' >>  Vagrantfile'
        ctx.sh 'echo \'end\' >> Vagrantfile'
        
        println("Generated Vagrantfile")
        ctx.sh 'cat Vagrantfile'
    }
    private def send_to_vm(String commands){
        ctx.sh "vagrant ssh -- -t '"+commands+"'"
    }
}
