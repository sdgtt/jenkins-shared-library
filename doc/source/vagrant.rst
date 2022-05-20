Vagrant Shared Library
======================

This is a jenkins project that uses Vagrant to build test environments. Virtual machines with different operating systems with MATLAB and Vivado installed.

In order to use those functions in a Jenkins pipeline you will need to add a library (Dashboard -> Manage Jenkins -> Configure System -> Global Pipeline Libraries) and following line at the beginging of the Jenkinsfile::

   @Library('library_name@branch_from_where_to_take_the_code')

check_for_box()
---------------

This function can be called by typing following code::

   boolean check = check_for_box("win")

Using the ``vagrant box list`` command, the function checks for Vagrant box called "win" and return "true" if it exists or "false" if there is no box with this name.

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/check_for_box.groovy>`_ for code.

check_for_snapshot()
--------------------

This function can be called by typing following code::

   boolean check = check_for_snapshot("initial-state")

Using the ``vagrant snapshot list`` command, the function checks for Vagrant snapshot called "initial-state" and return "true" if it exists or "false" if there is no snapshot with this name.

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/check_for_snapshot.groovy>`_ for code.

check_node()
------------

This function can be called by typing following code::

   name = check_node("win-vm")

This definition contains two functions:

* ``get_agents()``: return names of all online Jenkins agents.
* ``call()``: uses ``get_agents()`` to search for a Jenkins agent called "win-vm" and return the name or if nothing is found, function will display error logs.

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/check_node.groovy>`_ for code.

get_vagrant_vm_id()
-------------------

This function can be called by typing following code::

   vm_id = get_vagrant_vm_id("path/where/to/search/for/vm")

Using the ``vagrant global-status`` command, this function searches for a Vagrant VM at a specific location/workspace and returns its ID or a message if there is no VM.

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/get_vagrant_vm_id.groovy>`_ for code.

run_closure()
-------------

This function can be called by typing following code::

   run_closure(name, cls)

There are two parameters: "name" is the name of Jenkins agent installed inside Vagrant VM and "cls" is from closure.

This function runs a closure inside a Vagrant VM.

Let suppose that we have Vagrant VM at location "users/vagrant" and we want to run a closure inside it. We will use following Jenkinsfile for that and will return the host name::

   @Library('library_name@master')

   def test(String agent, Closure cls){
    try {
         vm_id = get_vagrant_vm_id("users/vagrant")
         println("Found existing vagrant vm id: " +vm_id)
         sh "VAGRANT_DEFAULT_PROVIDER=virtualbox vagrant up " +vm_id
         echo "Vagrant set up complete!"
         
         name = check_node('win-vm')
         run_closure(name, cls)
                
         sh 'vagrant halt'
      } catch (Exception e) {}
   }

   node("master") {
        stage("One"){
            test('master'){
                stage('test1'){
                    bat 'hostname'
                }
            }
         cleanWs()
        }
   }


Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/run_closure.groovy>`_ for code.

setup_jenkins_agent_on_vagrant_vm()
---------------------

This function can be called by typing following code::

   setup_jenkins_agent_on_vagrant_vm("<IP>")

This will run ssh commands to install a Jenkins agent inside Vagrant VM. As an argument there is the IP of the server where Vagrant VM is located.

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/setup_jenkins_agent.groovy>`_ for code.

setup_vagrant_box()
-------------------

This function can be called by typing following code::

   setup_vagrant_box("<boxaddress>", "<newboxname>", "<oldboxname>", "<vagrantfilepath>")

This will run ssh commands to download and add a box and copy a Vagrantfile in order to spin up a Vagrant VM.

As arguments there are:

* boxaddress: HTTP URL to a box
* newboxname: a new name for the box to be added
* oldboxname: the name of the box in HTTP URL address
* vagrantfilepath: an absolute path to a Vagrantfile on the current server to be used for Vagrant VM

Click `here <https://github.com/sdgtt/jenkins-shared-library/blob/add-vagrant-scripts/vars/setup_vagrant_box.groovy>`_ for code.

End user example
----------------

As a end user, this Jenkinsfile can be copied and pasted in "Pipeline definition" section of a Jenkins job (New Item -> Pipeline -> Pipeline script). Run the job after the requested informations are completed::

   @Library('test-lib@add-vagrant-scripts')

   def setup(String agent, Closure cls){
      try {
         trigger = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause).userUrl.toString()
         println(trigger)
         if (trigger == "<jenkins_path_to_place_where_the_job_is_created>") {
               vm_id = get_vagrant_vm_id("<jenkins_path_to_the_job>")

               //check for any vm in your workspace
               if (vm_id.contains("There")) {
                  echo "There is no vagrant environment."
                  echo "Setup vagrant."
                  
                  //if there is no box called "win", one will be downloaded from Artifactory
                  if (!check_for_box("win")) {
                     echo "There is no vagrant box! Download one."
                     setup_vagrant_box("https://artifactory.analog.com:443/artifactory/sdg-vagrant-dev/<name_of_the_box_you_want_to_download>.box", "win", "test_no_updates", "<path_to_your_Vagrantfile>")
                  }
                  
                  sh 'VAGRANT_DEFAULT_PROVIDER=virtualbox vagrant up'
                  
                  //a Jenkins agent will be installed on that vm
                  setup_jenkins_agent_on_vagrant_vm("<IP_of_jenkins_master")
                  echo "Vagrant set up complete!"
                  
                  //will use snapshots in order to can use the vm multiple times and to have it each time clear 
                  echo  "No snapshot! Save one!"
                  sh 'vagrant snapshot save default initial-state'
                  
                  //run code inside the vm
                  name = check_node('win-vm')
                  run_closure(name, cls)
                  
                  sh 'vagrant snapshot restore default initial-state'
               } else {
                  //if the vm is already set on your workspace
                  println("Found existing vagrant vm id: " +vm_id)
                  sh "VAGRANT_DEFAULT_PROVIDER=virtualbox vagrant up " +vm_id
                  echo "Vagrant set up complete!"
                  
                  //make sure that you have a snapshot of it and run the code
                  if (check_for_snapshot("initial-state")) {
                     name = check_node('win-vm')
                     run_closure(name, cls)
                     
                     sh 'vagrant snapshot restore default initial-state'
                  } else{
                     echo  "No snapshot! Save one!"
                     sh 'vagrant snapshot save default initial-state'
                  
                     name = check_node('win-vm')
                     run_closure(name, cls)
                  
                     sh 'vagrant snapshot restore default initial-state'
                  }
                  sh 'vagrant halt'
               }
         }
      } catch (Exception e) {}
      return true
   }

   node("master") {
         stage("One"){
               setup('master'){
                  stage('test1'){
                     //here you can insert the code you want to run inside vm
                     //bat '<YOUR_CODE>'
                     bat 'hostname'
                  }
               }
         }
   }
  
The result after this code is runned is::

   [Pipeline] node
   Running on win-vm-af45e971 in C:\jenkins\workspace\test
   [Pipeline] {
   [Pipeline] stage (hide)
   [Pipeline] { (test1)
   [Pipeline] bat

   C:\jenkins\workspace\test>hostname
   vagrant-10
   [Pipeline] }
   [Pipeline] // stage
   [Pipeline] }
   [Pipeline] // node
