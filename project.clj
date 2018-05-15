(defproject fatwheel "0.1.0-SNAPSHOT"
  :description "Fatwheel reloads code, runs tests, linters, and custom tasks when you save a file."
  :url "http://github.com/timothypratley/fatwheel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true}
  :aliases {"fatwheel" ["run" "-m" "fatwheel.core/-main"]}
  :plugins [[lein-cljsbuild "1.1.7"]]
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]

                 ;; ClojureScript deps (TODO: don't really want them in the classpath)
                 [org.clojure/clojurescript "1.10.238" :scope "provided"]
                 [reagent "0.8.0"]
                 ;; https://github.com/juxt/bidi/issues/182
                 [bidi "2.1.3" :exclusions [ring/ring-core]]
                 ;; ClojureScript and Clojure
                 [com.taoensso/sente "1.12.0"]

                 ;; Clojure deps
                 [org.clojure/tools.namespace "0.2.11"]
                 [compojure "1.6.1"]
                 [eftest "0.5.2"]
                 [http-kit "2.3.0"]
                 [integrant "0.6.3"]
                 [jonase/eastwood "0.2.6" :exclusions [org.clojure/clojure]]
                 [jonase/kibit "0.1.6"]
                 [prone "1.5.2"]
                 [rewrite-clj "0.6.0"]
                 [ring "1.6.3"]
                 [ring/ring-defaults "0.3.1"]]
  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :cljsbuild {:builds {:app {:source-paths ["src"]
                             :compiler {:main "fatwheel.client.main"
                                        :output-to "resources/public/js/compiled/app.js"
                                        :output-dir "resources/public/js/compiled/out"
                                        :asset-path "js/compiled/out"
                                        :externs ["externs.js"]}}}}
  :profiles
  {:dev
   {:env {:dev? true}
    :plugins [[lein-figwheel "0.5.16"]]
    :figwheel {:css-dirs ["resources/public/css"]}

    :cljsbuild {:builds {:app {:figwheel {:websocket-host "localhost"
                                          :on-jsload "fatwheel.client.main/mount-root"}
                               :compiler {:optimizations :none
                                          :source-map true
                                          :pretty-print true}}}}}
   :uberjar
   {:env {:production true}
    :hooks [leiningen.cljsbuild]
    :aot :all
    :omit-source true
    :cljsbuild {:builds {:app {:jar true
                               :compiler {:optimizations :advanced
                                          :infer-externs true
                                          :source-map "resources/public/js/compiled/app.js.map"
                                          :pretty-print false}}}}}})
