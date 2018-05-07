(defproject fatwheel "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [jonase/eastwood "0.2.5" :exclusions [org.clojure/clojure]]
                 [jonase/kibit "0.1.6"]
                 [rewrite-clj "0.6.0"]])
