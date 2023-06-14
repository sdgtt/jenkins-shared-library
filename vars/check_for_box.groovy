def call(String boxname) {
    result = sh(returnStdout: true ,script: "vagrant box list").trim()
    lines = result.split("\n")
    for( String line : lines ) {
        box = line.split("\\(")[0].trim()
        if (box == boxname){
            return true;
        }
    }
    return false;
}
