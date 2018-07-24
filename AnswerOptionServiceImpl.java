package com.sunlands.minilesson.user.service.impl;

import com.sunlands.minilesson.base.service.impl.ServiceImpl;
import com.sunlands.minilesson.curriculum.dao.IChapterDao;
import com.sunlands.minilesson.user.dao.IAnswerDao;
import com.sunlands.minilesson.curriculum.dao.IOptionDao;
import com.sunlands.minilesson.curriculum.dao.IQuizzesDao;
import com.sunlands.minilesson.curriculum.model.Chapter;
import com.sunlands.minilesson.curriculum.model.CheckPoint;
import com.sunlands.minilesson.curriculum.model.Option;
import com.sunlands.minilesson.curriculum.model.Quizzes;
import com.sunlands.minilesson.enums.OptionEnum;
import com.sunlands.minilesson.user.dao.IAnswerOptionDao;
import com.sunlands.minilesson.user.dao.ICurrentAnswerDao;
import com.sunlands.minilesson.user.dto.AnswerDto;
import com.sunlands.minilesson.user.model.Answer;
import com.sunlands.minilesson.user.model.AnswerOpetion;
import com.sunlands.minilesson.user.model.CurrentAnswer;
import com.sunlands.minilesson.user.service.IAnswerOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.util.List;

/**
 * @author zhangye
 */
@Service
public class AnswerOptionServiceImpl extends ServiceImpl<IAnswerOptionDao,AnswerOpetion,Long> implements IAnswerOptionService {

    @Autowired
    private IAnswerOptionDao answerOptionDao;
    @Autowired
    private ICurrentAnswerDao currentAnswerDao;
    @Autowired
    private IAnswerDao answerDao;
    @Resource
    private IQuizzesDao quizzesDao;
    @Resource
    private IOptionDao optionDao;

    @Autowired
    private IChapterDao chapterDao;

    @Override
    @Transactional(rollbackOn = Exception.class)
    public Answer saveAnswer(AnswerDto dto) {
        AnswerOpetion entity = getByDto(dto);
        Quizzes quizzes = quizzesDao.getOne(dto.getQuizzesId());
        if (quizzes!=null){
            OptionEnum optionEnum = OptionEnum.valueOf(dto.getOption());
            if (optionEnum.getCode().intValue()==quizzes.getQuestionKey()){
                entity.setRight(true);
            }
        }
        answerOptionDao.save(entity);
        entity.setTimes(getSaveTimes(entity));
        Answer answer = saveAnswers(entity);
        entity.setAnswer(answer);
        saveCurrentAnswer(entity);
        return answer;
    }

    @Transactional(rollbackOn = Exception.class)
    public Integer getSaveTimes(AnswerOpetion entity) {

        Option option = optionDao.getOne(entity.getOption().getId());
        entity.setOption(option);
        Chapter chapter =option.getQuizzes().getCheckPoint().getChapter();
        entity.setChapter(chapter);

        Integer times = getTimes(entity.getOpenId(),chapter.getId());
        Long acount = answerOptionDao.getCount(entity.getOpenId(),chapter.getId(),times);
        Integer qcount = getQuizzes(chapter);
        //如果上一遍已经完成
        if (acount!=null && acount.intValue()==qcount) {
            times += 1;
        }
        return times;
    }
    @Transactional(rollbackOn = Exception.class)
    public void saveCurrentAnswer(AnswerOpetion entity) {
        CurrentAnswer currentAnswer = currentAnswerDao.getCurrentQuzziesByCPID(entity.getOpenId(),
                entity.getOption().getQuizzes().getCheckPoint().getId());
        if(currentAnswer == null) {
            currentAnswer = new CurrentAnswer();
            currentAnswer.setOpenId(entity.getOpenId());
            currentAnswer.setCheckPoint(entity.getOption().getQuizzes().getCheckPoint());
            currentAnswerDao.save(currentAnswer);
        }
        currentAnswer.setCurrentQuizzes(entity.getOption().getQuizzes());
    }
    @Transactional(rollbackOn = Exception.class)
    public Answer saveAnswers(AnswerOpetion entity) {
       Answer answer = answerDao.getByOpenIdAndChapterIdAndTimes(entity.getOpenId(),
               entity.getChapter().getId(),
               entity.getTimes()
       );
       if(answer == null) {
           answer = new Answer();
           answer.setChapter(entity.getChapter());
           answer.setOpenId(entity.getOpenId());
           answer.setTimes(entity.getTimes());
           answerDao.save(answer);
       }
       return answer;
    }
    /**
     * 查询随堂考總數量
     * @param chapter 考点列表
     * @return 章節下所有随堂考數量
     */
    @Override
    public Integer getQuizzes(Chapter chapter) {
        List<CheckPoint> cps = chapter.getCps();
        Integer count = 0;
        if(cps!=null && cps.size()>0) {
            for (CheckPoint cp:cps) {
                List<Quizzes> qs = cp.getQs();
                if(qs!=null && qs.size()>0) {
                    count += qs.size();
                }
            }
        }
        return count;
    }

    /**
     * 查询当前属于第几遍答题
     * @param openId 当前学生
     * @param chapterId 当前章节
     * @return 第几遍答题
     */
    @Override
    public Integer getTimes(String openId, Long chapterId) {
        Long as = answerDao.getCountByOpenIdAndChapterId(openId,chapterId);
        Integer times = AnswerOpetion.FIRST_TIMES;
        if(as !=null && as>0) {
            times = as.intValue();
        }
        return times;
    }
    /*@Override
    public Integer getTimes(String openId, Long chapterId) {
        List<Answer> as = answerDao.getByOpenIdAndChapterId(openId,chapterId);
        Integer times = AnswerOpetion.FIRST_TIMES;
        if(as !=null) {
           if(as.size()==1) {
                Long acount = answerOptionDao.getCount(openId,chapterId,1);
                Chapter entity = chapterDao.getOne(chapterId);
                Integer qcount = getQuizzes(entity);
                if (acount!=null && acount.intValue()==qcount) {
                    times = AnswerOpetion.LAST_TIMES;
                }
            } else if(as.size()==2) {
               times = AnswerOpetion.LAST_TIMES;
                Long acount = answerOptionDao.getCount(openId,chapterId,2);
                Chapter entity = chapterDao.getOne(chapterId);
                Integer qcount = getQuizzes(entity);
                if (acount!=null && acount.intValue()==qcount) {
                    times = AnswerOpetion.NON_TIMES;
                }

            }
        }
        return times;
    }*/
    AnswerOpetion getByDto(AnswerDto dto) {
        AnswerOpetion ao = new AnswerOpetion();
        ao.setOpenId(dto.getOpenId());
        ao.setOption(new Option(dto.getOptionId()));
        return ao;
    }

    @Override
    public Long getCountByOpenId(String openId,Long chapterId){
        Integer times = getTimes(openId,chapterId);
        return answerOptionDao.getCount(openId,chapterId,times);
    }

    @Override
    public Long getRightCountByOpenId(String openId, Long chapterId) {
        Integer times = getTimes(openId,chapterId);
        Long acount = answerOptionDao.getCount(openId,chapterId,times);
        Integer qcount = getQuizzes(chapterDao.getOne(chapterId));
        if(times != AnswerOpetion.FIRST_TIMES) {
            if (acount!=null) {
                //如果本遍未完成
                if(acount.intValue()<qcount) {
                    acount = answerOptionDao.getRightCount(openId,true,chapterId,times-1);
                } else {
                    acount = answerOptionDao.getRightCount(openId,true,chapterId,times);
                }
            }
        } else {
            //第一次未完成
            if (acount!=null && acount.intValue()<qcount) {
                acount = 0l;
            } else {
                //如果已完成则返回本次回答正确的数量
                acount = answerOptionDao.getRightCount(openId,true,chapterId,times);
            }
        }
        return acount;
    }

    @Override
    public Long getCountByOpenId(String openId, Long chapterId, Integer times) {
        return answerOptionDao.getCount(openId,chapterId,times);
    }
}
