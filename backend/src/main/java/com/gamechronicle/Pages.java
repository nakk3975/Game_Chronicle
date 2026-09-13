package com.gamechronicle;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class Pages {
 @GetMapping({"/dashboard","/timeline","/library","/analytics","/settings"}) public String spa(){return "forward:/index.html";}
}
