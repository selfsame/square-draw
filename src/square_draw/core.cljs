(ns ^:figwheel-hooks square-draw.core
  (:require
    [reagent.core :as r]
    [reagent.dom :as rd]))

(defonce state (r/atom {
  :tool :draw
  :file [
    {:x 20 :y 20 :w 100 :h 60 :fill "#ff3300"}
    ]
  :selected 0
  }))



(defonce canvas (js/document.querySelector "canvas"))
(defonce ctx (.getContext canvas "2d"))

(defn render! []
  (.clearRect ctx 0 0 (.-width canvas) (.-height canvas))
  (dorun
    (for [el (:file @state)]
      (do 
        (set! (.-fillStyle ctx) (:fill el "silver"))
        (.fillRect ctx (:x el) (:y el) (:w el) (:h el)))))
  (when-let [el (get (:file @state) (:selected @state))]
    (.setLineDash ctx #js [6])
    (.strokeRect ctx (:x el) (:y el) (:w el) (:h el))
  )
  )

; ideally this would schedule render! in a requestAnimationFrame
(add-watch state :render
  (fn [k r o n]
    (when (or (not= (:file o) (:file n))
              (not= (:selected o) (:selected n)))
      (render!))))



(defn mouse->canvas [x y]
  ; hardcoded in the css
  [(- x 100) (- y 12)])

(defn pick [cx cy]
  ; return a vector of every element overlapping the given coords
  (let [[cx cy] (mouse->canvas cx cy)]
    (filterv 
      (fn [[idx {:keys [x y w h]}]]
        (and (< x cx (+ x w))
             (< y cy (+ y h))))
      (map-indexed vector (:file @state)))))

(defn on-draw [e]
  ; note the initial mouse x/y and create a new element in the file vector
  ; then set up a mouse move fn that modifies this new element's width based on the current mouse position
  (let [[x y] (mouse->canvas (.-x e) (.-y e))
        idx (count (:file @state))]
    (swap! state update :file conj {:x x :y y :w 0 :h 0 :fill "orange"})
    (swap! state assoc :selected idx)
    (swap! state assoc :movefn 
      (fn [e]
        (let [[mx my] (mouse->canvas (.-x e) (.-y e))
              el (get-in @state [:file idx])]
          (swap! state update-in [:file idx] assoc :w (- mx x) :h (- my y)))
        ))
    ))

(defn on-move [e]
  (if-let [[idx _] (first (pick (.-x e) (.-y e)))]
    (do (swap! state assoc :selected idx)
      )
    (swap! state assoc :selected nil)
    ))

; Input cycles, dispatch on mouse down based on the current tool.  If move/up handlers are needed
; they can be set up in the dispatched fn

(defn pointer-down [e]
  (js/console.log e)
  (prn (pick (.-x e) (.-y e)))
  (when (= (.-target e) canvas)
    (cond
      (= (:tool @state) :draw) (on-draw e)
      (= (:tool @state) :move) (on-move e)
      )))

(defn pointer-move [e]
  (if (:movefn @state) ((:movefn @state) e)))

(defn pointer-up [e]
  (js/console.log e)
  (if (:upfn @state) ((:upfn @state) e))
  (swap! state dissoc :movefn :upfn))

(js/addEventListener "pointerdown" pointer-down)
(js/addEventListener "pointermove" pointer-move)
(js/addEventListener "pointerup"   pointer-up)


; UI

(defn toolbar-ui []
  [:div.toolbar
    (into [:div]
      (map 
        (fn [k] 
          [:div.tool 
            {:class (if (= (:tool @state) k) "active")
             :on-click (fn [e] 
              (swap! state assoc :tool k)
              (js/document.body.setAttribute "class" (name k)))}
            k])
        [:move :draw]))])

(defn root-ui []
  [:div
    [toolbar-ui]])

(rd/render [root-ui] (js/document.querySelector "#app"))

(render!)