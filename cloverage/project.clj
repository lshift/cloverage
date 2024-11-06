(def clojure-profile (if (System/getenv "CI")
                       (-> "CLOJURE_VERSION" System/getenv not-empty (doto (assert "CLOJURE_VERSION is unset!")))
                       "1.12"))

(defproject cloverage "1.3.0-SNAPSHOT"
  :description "Form-level test coverage for clojure."
  :url "https://www.github.com/cloverage/cloverage"
  :scm {:name "git"
        :dir ".."
        :url "https://www.github.com/cloverage/cloverage"
        :tag "HEAD"}
  :vcs :git
  :main ^:skip-aot cloverage.coverage
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :dependencies [[org.clojure/clojure "1.12.0" :scope "provided"]
                 [org.clojure/tools.reader "1.5.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [org.clojure/tools.logging "1.3.0"]
                 [org.clojure/tools.namespace "1.5.0"]
                 [org.clojure/java.classpath "1.1.0"]
                 [org.clojure/data.xml "0.2.0-alpha6"]
                 [org.clojure/data.json "2.5.0" :exclusions [org.clojure/clojure]]
                 [riddley "0.2.0"]
                 [slingshot "0.12.2"]]
  :profiles {:dev {:aot ^:replace []
                   :plugins [[dev.weavejester/lein-cljfmt "0.13.0"]
                             [lein-kibit "0.1.11"]]
                   :global-vars {*warn-on-reflection* true}
                   :resource-paths ["dev-resources"]
                   :source-paths ["repl" "sample"]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :1.12 {:dependencies [[org.clojure/clojure "1.12.0"]]}
             :eastwood {:plugins [[jonase/eastwood "1.2.3"]]
                        :eastwood {:ignored-faults {:implicit-dependencies {cloverage.report.emma-xml ~(case clojure-profile
                                                                                                         ("1.8" "1.9") [{:line 42}]
                                                                                                         [])}}}}
             :clj-kondo {:plugins [[com.github.clj-kondo/lein-clj-kondo "2024.09.27"]]}
             :humane {:dependencies [[pjstadig/humane-test-output "0.11.0"]]
                      :injections [(require 'pjstadig.humane-test-output)
                                   (pjstadig.humane-test-output/activate!)]}
             :test {:aot ^:replace []
                    :dependencies [[org.slf4j/slf4j-simple "2.0.16"]
                                   [lambdaisland/kaocha "1.91.1392" :exclusions [org.clojure/tools.cli
                                                                                 org.clojure/clojure
                                                                                 org.clojure/spec.alpha]]
                                   [lambdaisland/kaocha-cloverage "1.1.89" :exclusions [org.clojure/clojure
                                                                                        org.clojure/spec.alpha]]]
                    :source-paths ["sample"]
                    :jvm-opts ["-Duser.language=en-US"]}
             :ci {}}
  :aliases {"all" ["with-profile" "+1.8:+1.9:+1.10:+1.11:+1.12"]
            "kaocha" ["test-ci"]
            "kondo-deps" ["with-profile"
                          "+dev,+test,+ci,+clj-kondo"
                          "clj-kondo"
                          "--copy-configs"
                          "--dependencies"
                          "--lint"
                          "$classpath"]
            "kondo-ci" ["do" ["kondo-deps"]
                        ["with-profile"
                         ~(str "+dev,+test,+ci,+clj-kondo,+" clojure-profile)
                         "clj-kondo"
                         "--lint"
                         "src" "test"]]
            "eastwood-ci" ["with-profile"
                           ~(str "-dev,+test,+ci,+eastwood,+" clojure-profile)
                           "eastwood"]
            "test-ci" ~(if (= "1.8" clojure-profile)
                         ["with-profile"
                          (str "-dev,+test,+ci,+" clojure-profile)
                          "test"]
                         ["with-profile"
                          (str "-dev,+test,+ci,+" clojure-profile)
                          "run"
                          "-m"
                          "kaocha.runner"])})
