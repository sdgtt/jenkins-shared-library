def call(String workspace){
    result = sh(returnStdout: true ,script: "vagrant global-status").trim()
    lines = result.split("\n")
    len = lines.length
    for(int i = 2;i<len;i++) {
        if (lines[i].contains("workspace") || lines[i].contains("There are no active Vagrant environments on this computer!")){
            id = lines[i].substring(0,7);
            return id;
        }
    }
}
