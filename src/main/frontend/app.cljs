(ns frontend.app
  (:require [cljs.core.async :as a]
            [cognitect.transit :as t]))

(def submit-chan (a/chan))

(defn start-loop
  [submit-chan]
  (a/go
    (loop []
      (when-let [submission (a/<! submit-chan)]
        (prn "received" submission)
        (recur)))))

(defonce twriter (t/writer :json))
(defonce treader (t/reader :json))

(defn post-message
  [worker message]
  (.postMessage worker (t/write twriter message)))

(defn create-worker
  ([]
   (create-worker "/js/worker.js"))
  ([worker-file]
   (let [worker (js/Worker. worker-file)
         echo-handler (fn [e] (prn (t/read treader (.. e -data))))]
     (.. worker (addEventListener "message" echo-handler))
     (post-message worker {:type :init :data "some init data"})
     worker)))

(defn init []
  (println "Hello World")
  (let [worker (create-worker)]
    (post-message worker {:type :validate
                          :test "hello world"})))

;; Example from https://shadow-cljs.github.io/docs/UsersGuide.html#_web_workers
;; (defn init []
;;   (let [worker (js/Worker. "/js/worker.js")]
;;     (.. worker (addEventListener "message" (fn [e] (js/console.log e))))
;;     (.. worker (postMessage "hello world"))))

(comment
  (prn "test")

  (test-roundtrip)

  (start-loop submit-chan)
  (a/put! submit-chan {:test 'asdf})

  (t/write twriter {:test "hello world"})

  )

;; Test that transit is working
(defn roundtrip [x]
  (let [w (t/writer :json)
        r (t/reader :json)]
    (t/read r (t/write w x))))

(defn test-roundtrip []
  (let [list1 [:red :green :blue]
        list2 [:apple :pear :grape]
        data  {(t/integer 1) list1
               (t/integer 2) list2}
        data' (roundtrip data)]
    (prn data)
    (assert (= data data'))))
