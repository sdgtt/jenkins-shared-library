def call(String snapshotname) {
    result = sh(returnStdout: true ,script: "vagrant snapshot list").trim()
    lines = result.split("\n")
    for( String line : lines ) {
        snapshot = line.split("\\(")[0].trim()
        if (snapshot == snapshotname){
            println("Found existing snapshot: "+line)
            return true;
        }
    }
    return false;
}
