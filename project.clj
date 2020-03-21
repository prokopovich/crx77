(defproject crx77 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"],
                [org.clojure/tools.deps.alpha "0.8.677"],
                [juxt/crux-core "20.03-1.8.0-alpha"],
                [org.slf4j/slf4j-log4j12 "1.7.9"]              
  ]
  :main ^:skip-aot crx77.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
