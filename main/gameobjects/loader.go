components {
  id: "loader"
  component: "/main/scripts/loader.script"
}
embedded_components {
  id: "login_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/collections/login.collection\"\n"
  ""
}
embedded_components {
  id: "game_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/scripts/ghscripts/game.collection\"\n"
  ""
}
embedded_components {
  id: "quad_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/Quad/main/quad_socket.collection\"\n"
  ""
}
embedded_components {
  id: "pong_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/Pong/main/pong_proxy.collection\"\n"
  ""
}
embedded_components {
  id: "puzzle_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/Puzzle/main/puzzle_proxy.collection\"\n"
  ""
}
embedded_components {
  id: "space_shooter_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/spaceShooterGame-main/main/space_shooter_proxy.collection\"\n"
  ""
}
embedded_components {
  id: "ttt_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/ttt/ttt_socket.collection\"\n"
  ""
}
embedded_components {
  id: "runner_proxy"
  type: "collectionproxy"
  data: "collection: \"/main/runner_stan-main/main/main.collection\"\n"
  ""
}
