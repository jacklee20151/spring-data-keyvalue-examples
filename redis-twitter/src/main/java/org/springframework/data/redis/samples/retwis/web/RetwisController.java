/*
 * Copyright 2011 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.redis.samples.retwis.web;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.samples.Post;
import org.springframework.data.redis.samples.retwis.Range;
import org.springframework.data.redis.samples.retwis.RetwisSecurity;
import org.springframework.data.redis.samples.retwis.redis.RedisTwitter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Annotation-driven controller for Retwis.
 * 
 * @author Costin Leau
 */
@Controller
public class RetwisController {

	@Autowired
	private final RedisTwitter twitter;

	@Autowired
	public RetwisController(RedisTwitter twitter) {
		this.twitter = twitter;
	}

	@RequestMapping("/")
	public String root() {
		// FIXME: redirect from / authenticated users
		// land page is signin
		// in case the user is authenticated, the cookie should interceptor redirects accordingly
		return "signin";
	}

	@RequestMapping("/signUp")
	public String signUp(@RequestParam String name, @RequestParam String pass, @RequestParam String pass2, HttpServletResponse response) {
		// FIXME: add pass check
		String auth = twitter.addUser(name, pass);
		addAuthCookie(auth, name, response);

		return "redirect:/!" + name;
	}

	@RequestMapping("/signIn")
	public String signIn(@RequestParam String name, @RequestParam String pass, HttpServletResponse response) {
		// add tracing cookie
		if (twitter.auth(name, pass)) {
			addAuthCookie(twitter.addAuth(name), name, response);
			return "redirect:/!" + name;
		}
		// go back to sign in screen
		return "signin";
	}

	private void addAuthCookie(String auth, String name, HttpServletResponse response) {
		RetwisSecurity.setUser(name, twitter.findUid(name));

		Cookie cookie = new Cookie(CookieInterceptor.RETWIS_COOKIE, auth);
		cookie.setComment("Retwis-J demo");
		// cookie valid for up to 1 week
		cookie.setMaxAge(60 * 60 * 24 * 7);
		response.addCookie(cookie);
	}

	@RequestMapping(value = "/!{name}", method = RequestMethod.GET)
	public ModelAndView posts(@PathVariable String name) {
		checkUser(name);

		String targetUid = twitter.findUid(name);
		ModelAndView mav = new ModelAndView("home");
		mav.addObject("post", new Post());
		mav.addObject("name", name);
		mav.addObject("posts", twitter.getPosts(targetUid, new Range()));
		mav.addObject("followers", twitter.getFollowers(targetUid));
		mav.addObject("following", twitter.getFollowing(targetUid));
		if (RetwisSecurity.isSignedIn()) {
			mav.addObject("also_followed", twitter.alsoFollowed(RetwisSecurity.getUid(), targetUid));
			mav.addObject("common_followers", twitter.commonFollowers(RetwisSecurity.getUid(), targetUid));
			mav.addObject("follows", twitter.isFollowing(RetwisSecurity.getUid(), targetUid));
		}
		return mav;
	}

	@RequestMapping(value = "/!{name}", method = RequestMethod.POST)
	public ModelAndView posts(@PathVariable String name, @ModelAttribute Post post) {
		checkUser(name);

		twitter.post(name, post);
		return posts(name);
	}

	@RequestMapping("/!{name}/follow")
	public String follow(@PathVariable String name) {
		checkUser(name);

		twitter.follow(name);
		return "redirect:/!" + name;
	}

	@RequestMapping("/!{name}/stopfollowing")
	public String stopFollowing(@PathVariable String name) {
		checkUser(name);

		twitter.stopFollowing(name);
		return "redirect:/!" + name;
	}

	@RequestMapping("/!{name}/mentions")
	public ModelAndView mentions(@PathVariable String name) {
		checkUser(name);

		ModelAndView mav = new ModelAndView("mentions");
		mav.addObject("name", name);
		mav.addObject("posts", twitter.getMentions(twitter.findUid(name), new Range()));
		return mav;
	}

	@RequestMapping("/timeline")
	public Map timeline() {
		Map map = new HashMap();
		map.put("posts", twitter.timeline(new Range()));
		map.put("users", twitter.newUsers(new Range()));
		return map;
	}

	@RequestMapping("/logout")
	public String logout() {
		String user = RetwisSecurity.getName();
		// invalidate auth
		twitter.deleteAuth(user);
		return "redirect:/";
	}

	private void checkUser(String username) {
		if (!twitter.isUserValid(username)) {
			throw new NoSuchUserException(username);
		}
	}

	@ExceptionHandler(NoSuchUserException.class)
	public ModelAndView handleNoUserException(NoSuchUserException ex, HttpServletRequest request) {
		ModelAndView mav = new ModelAndView("nouser");
		mav.addObject("user", ex.getUsername());
		return mav;
	}
}