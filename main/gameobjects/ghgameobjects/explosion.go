components {
  id: "explosion"
  component: "/main/scripts/ghscripts/explosion.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"explode\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/atlases/ghatlases/sprites.atlas\"\n"
  "}\n"
  ""
  scale {
    x: 0.5
    y: 0.5
    z: 1.0E-6
  }
}
