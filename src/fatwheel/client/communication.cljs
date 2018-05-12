(ns fatwheel.client.communication
  (:require [taoensso.sente :as sente :refer (cb-success?)]))

(defn init [app-state]
  (let [{:keys [ch-recv send-fn]} (sente/make-channel-socket! "/chsk" {})]
    (sente/start-client-chsk-router!
      ch-recv
      (fn handle-message [{:keys [id ?data]}]
        (println "GOT" id ?data)
        (case id
          :chsk/state (if (:first-open? ?data)
                        (println "Channel socket successfully established!")
                        (println "Channel socket state change:" ?data))
          :chsk/recv (let [[message-type msg] ?data]
                       (case message-type
                         :chsk/ws-ping (println "ping")
                         :fatwheel/app-state (swap! app-state :server-state msg)
                         (println "Unhandled message-type:" message-type msg)))
          :chsk/handshake (println "Handshake:" ?data)
          (println "Unhandled event-id:" id ?data))))))
