def call(String boxaddress,String newboxname, String oldboxname, String vagrantfilepath){
    sh "wget --continue --tries=0 '${boxaddress}'"
    sh "vagrant box add '${newboxname}' '${oldboxname}'.box --force"
    sh "cp '${vagrantfilepath}' ."
}
