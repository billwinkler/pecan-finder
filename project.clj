(defproject pecan-finder "0.1.0-SNAPSHOT"
  :description "An experiment with the Google Cloud Vision object localization api"
  :url "https://github.com/billwinkler/pecan-finder"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.google.cloud/google-cloud-vision "3.1.2"]]
  :bom {:import [[com.google.cloud/libraries-bom "26.1.3"]]}
  :repl-options {:init-ns pecan-finder.quickstart}
  :java-source-paths ["src/pecan_finder/java"])
