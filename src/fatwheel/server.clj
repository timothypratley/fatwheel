(ns fatwheel.server
  (:require [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]))

(defn ws-routes [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]}]
  (routes
    (GET "/" req (response/redirect "/index.html"))
    (GET "/chsk" req (ajax-get-or-ws-handshake-fn req))
    (POST "/chsk" req (ajax-post-fn req))
    (route/resources "/")
    (route/not-found "Not found")))

(defn handler [app-routes]
  ;; the static middleware will bypass sessions and therefore disable anti-forgery
  ;; https://github.com/ring-clojure/ring-defaults/issues/23
  (wrap-defaults app-routes (dissoc site-defaults :static)))

(defn start [event-msg-handler opts]
  {:pre [(fn? event-msg-handler)]}
  (println "Starting server...")
  (let [ws (sente/make-channel-socket! (get-sch-adapter) {})
        r (ws-routes ws)
        h (handler r)
        stop-router (sente/start-server-chsk-router! (:ch-recv ws) event-msg-handler)
        stop-server (run-server h opts)]
    (assoc ws :stop (fn stop []
                      (stop-router)
                      (stop-server :timeout 100)))))

;; TODO: pretty boring helpers, delete them
(defn stop [{:keys [stop]}]
  (stop))

(defn ws-send [{:keys [send-fn connected-uids]} msg]
  (doseq [uid (:any @connected-uids)]
    (send-fn uid msg)))

(defn ch-recv [{:keys [ch-recv]}]
  (ch-recv))

(defn connected-uids [{:keys [connected-uids]}]
  connected-uids)