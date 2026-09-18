def call(className){
    def classPath = "../src/sdg/stages"
    def classLoader = new GroovyClassLoader()
    classLoader.addClasspath(classPath)
    
    // load stage class
    def stageClass = classLoader.loadClass("sdg.stages.${className}")
    return stageClass.newInstance()
}