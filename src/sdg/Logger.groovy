package sdg

import sdg.Gauntlet

class Logger {

    private def gauntlet

    def Logger(Gauntlet gauntlet){
        this.gauntlet = gauntlet
    }

    public void info(String message) {
        if(gauntlet.get_env("debug_level") >= 3){
            gauntlet.stepExecutor.echo "[INFO] ${message}"
        }
    }

    public void warning(String message) {
        if(gauntlet.get_env("debug_level") >= 2){
            gauntlet.stepExecutor.echo "[WARNING] ${message}"
        }
    }

    public void error(String message) {
        if(gauntlet.get_env("debug_level") >= 1){
            gauntlet.stepExecutor.echo "[ERROR] ${message}"
        }
    }
}