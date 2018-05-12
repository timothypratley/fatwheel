(ns fatwheel.server
  (:require [compojure.core :refer [routes GET POST]]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]))

(defn login-handler
  [req]
  (let [{:keys [session params]} req
        {:keys [user-id]} params]
    (println "Login request: " params)
    {:status 200 :session (assoc session :uid user-id)}))

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

;; TODO: move to another ns? connect with integrant?
(defmulti -event-msg-handler :id)

;; TODO: Wraps `-event-msg-handler` with logging, error catching, etc.
(defn event-msg-handler [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defn start [opts]
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
    (send-fn uid [:fatwheel/app-state msg])))

(defn ch-recv [{:keys [ch-recv]}]
  (ch-recv))

(defn connected-uids [{:keys [connected-uids]}]
  connected-uids)


;; TODO: send initial state on connect


(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)]
    (println "Unhandled event:" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))
